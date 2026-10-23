pub mod secrets_generated;

use hmac::{Hmac, Mac};
use jni::objects::{JClass, JObject, JString};
use jni::sys::jstring;
use jni::JNIEnv;
use sha2::{Digest, Sha256};
use std::fs::File;
use std::io::{BufRead, BufReader};
use std::time::{SystemTime, UNIX_EPOCH};
use zeroize::Zeroize;

type HmacSha256 = Hmac<Sha256>;

fn sha256_digest(data: &[u8]) -> [u8; 32] {
    let mut hasher = Sha256::new();
    hasher.update(data);
    hasher.finalize().into()
}

fn hmac_sha256_digest(key: &[u8], msg: &[u8]) -> [u8; 32] {
    let mut mac = HmacSha256::new_from_slice(key).expect("HMAC can take key of any size");
    mac.update(msg);
    mac.finalize().into_bytes().into()
}

fn derive_keystream(key: &[u8], salt: &[u8], length: usize) -> Vec<u8> {
    let mut generated = 0;
    let mut counter: u32 = 0;
    let mut out = Vec::with_capacity(length);

    while generated < length {
        let mut msg = Vec::with_capacity(salt.len() + 4);
        msg.extend_from_slice(salt);
        msg.extend_from_slice(&counter.to_be_bytes());

        let block = hmac_sha256_digest(key, &msg);
        let to_copy = std::cmp::min(block.len(), length - generated);
        out.extend_from_slice(&block[..to_copy]);
        generated += to_copy;
        counter = counter.wrapping_add(1);
    }
    out
}

fn is_being_debugged() -> bool {
    if let Ok(file) = File::open("/proc/self/status") {
        let reader = BufReader::new(file);
        for line in reader.lines().flatten() {
            if let Some(rest) = line.strip_prefix("TracerPid:") {
                let pid_str = rest.trim();
                if let Ok(pid) = pid_str.parse::<i32>() {
                    return pid != 0;
                }
            }
        }
    }
    false
}

fn is_debuggable(env: &mut JNIEnv, context: &JObject) -> bool {
    let Ok(app_info) = env.call_method(
        context,
        "getApplicationInfo",
        "()Landroid/content/pm/ApplicationInfo;",
        &[],
    ) else {
        return false;
    };
    let Ok(app_info_obj) = app_info.l() else {
        return false;
    };
    if app_info_obj.is_null() {
        return false;
    }

    let Ok(flags) = env.get_field(&app_info_obj, "flags", "I") else {
        return false;
    };
    let Ok(flags_val) = flags.i() else {
        return false;
    };

    const FLAG_DEBUGGABLE: i32 = 0x00000002;
    (flags_val & FLAG_DEBUGGABLE) != 0
}

fn verify_caller_signature(
    env: &mut JNIEnv,
    context: &JObject,
) -> Result<[u8; 32], ()> {
    if context.is_null() {
        return Err(());
    }

    let pkg_name_val = env
        .call_method(context, "getPackageName", "()Ljava/lang/String;", &[])
        .map_err(|_| ())?;
    let pkg_name_obj = pkg_name_val.l().map_err(|_| ())?;
    if pkg_name_obj.is_null() {
        return Err(());
    }
    let pkg_name_jstr: JString = pkg_name_obj.into();
    let pkg_name: String = env.get_string(&pkg_name_jstr).map_err(|_| ())?.into();

    if pkg_name != "com.neonsong.app" {
        return Err(());
    }

    let is_placeholder = secrets_generated::EXPECTED_CERT_PREFIX.starts_with("PLACEHOLDER");
    if !is_placeholder {
        if is_debuggable(env, context) || is_being_debugged() {
            return Err(());
        }
    } else {
        const DEV_FALLBACK: &[u8] = b"NEONSONG_DEV_FALLBACK_KEY";
        return Ok(sha256_digest(DEV_FALLBACK));
    }

    // Production cert verification
    let pm_val = env
        .call_method(
            context,
            "getPackageManager",
            "()Landroid/content/pm/PackageManager;",
            &[],
        )
        .map_err(|_| ())?;
    let pm_obj = pm_val.l().map_err(|_| ())?;

    // Try GET_SIGNING_CERTIFICATES (0x08000000), fallback to GET_SIGNATURES (64)
    let pkg_info = env
        .call_method(
            &pm_obj,
            "getPackageInfo",
            "(Ljava/lang/String;I)Landroid/content/pm/PackageInfo;",
            &[(&pkg_name_jstr).into(), 0x08000000i32.into()],
        )
        .or_else(|_| {
            let _ = env.exception_clear();
            env.call_method(
                &pm_obj,
                "getPackageInfo",
                "(Ljava/lang/String;I)Landroid/content/pm/PackageInfo;",
                &[(&pkg_name_jstr).into(), 64i32.into()],
            )
        })
        .map_err(|_| ())?
        .l()
        .map_err(|_| ())?;

    if pkg_info.is_null() {
        return Err(());
    }

    // Extract signature bytes
    let sig_bytes = extract_first_signature_bytes(env, &pkg_info)?;
    let hash = sha256_digest(&sig_bytes);

    let hex_hash: String = hash.iter().map(|b| format!("{b:02x}")).collect();
    let expected = secrets_generated::EXPECTED_CERT_PREFIX;

    if hex_hash.len() != 64 || expected.len() != 64 {
        return Err(());
    }

    // Constant-time comparison
    let mut diff = 0u8;
    for (a, b) in hex_hash.bytes().zip(expected.bytes()) {
        diff |= a ^ b;
    }

    if diff != 0 {
        return Err(());
    }

    Ok(hash)
}

fn extract_first_signature_bytes(env: &mut JNIEnv, pkg_info: &JObject) -> Result<Vec<u8>, ()> {
    // Check signingInfo first (Android 9+)
    if let Ok(signing_info_field) =
        env.get_field(pkg_info, "signingInfo", "Landroid/content/pm/SigningInfo;")
    {
        if let Ok(signing_info) = signing_info_field.l() {
            if !signing_info.is_null() {
                if let Ok(certs_val) = env.call_method(
                    &signing_info,
                    "getApkContentsSigners",
                    "()[Landroid/content/pm/Signature;",
                    &[],
                ) {
                    if let Ok(certs_obj) = certs_val.l() {
                        if !certs_obj.is_null() {
                            let certs_array = unsafe { jni::objects::JObjectArray::from_raw(certs_obj.into_raw()) };
                            if let Ok(len) = env.get_array_length(&certs_array) {
                                if len > 0 {
                                    if let Ok(sig) = env.get_object_array_element(&certs_array, 0) {
                                        return signature_to_bytes(env, &sig);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    let _ = env.exception_clear();

    // Fallback: signatures array
    if let Ok(sig_field) = env.get_field(pkg_info, "signatures", "[Landroid/content/pm/Signature;") {
        if let Ok(signatures_obj) = sig_field.l() {
            if !signatures_obj.is_null() {
                let sig_array = unsafe { jni::objects::JObjectArray::from_raw(signatures_obj.into_raw()) };
                if let Ok(len) = env.get_array_length(&sig_array) {
                    if len > 0 {
                        if let Ok(sig) = env.get_object_array_element(&sig_array, 0) {
                            return signature_to_bytes(env, &sig);
                        }
                    }
                }
            }
        }
    }
    let _ = env.exception_clear();
    Err(())
}

fn signature_to_bytes(env: &mut JNIEnv, sig: &JObject) -> Result<Vec<u8>, ()> {
    let bytes_val = env.call_method(sig, "toByteArray", "()[B", &[]).map_err(|_| ())?;
    let bytes_obj = bytes_val.l().map_err(|_| ())?;
    let byte_array = unsafe { jni::objects::JByteArray::from_raw(bytes_obj.into_raw()) };
    let len = env.get_array_length(&byte_array).map_err(|_| ())? as usize;
    let mut out = vec![0i8; len];
    env.get_byte_array_region(&byte_array, 0, &mut out).map_err(|_| ())?;
    Ok(out.into_iter().map(|b| b as u8).collect())
}

fn parse_addon_url(url: &str) -> Option<(&str, &str)> {
    let path_start = if let Some(scheme_end) = url.find("://") {
        url[scheme_end + 3..].find('/').map(|i| scheme_end + 3 + i)?
    } else {
        url.find('/')?
    };

    let path_and_query = &url[path_start..];
    let path = match path_and_query.find(['?', '#']) {
        Some(idx) => &path_and_query[..idx],
        None => path_and_query,
    };

    let a_pos = path.find("/a/")?;
    let tok_start = a_pos + 3;
    let token = match path[tok_start..].find('/') {
        Some(end) => &path[tok_start..tok_start + end],
        None => &path[tok_start..],
    };

    if token.is_empty() {
        None
    } else {
        Some((path, token))
    }
}

fn reconstruct_client_secret(cert_hash: &[u8; 32]) -> Option<Vec<u8>> {
    use secrets_generated::*;

    if CLIENT_SECRET_FRAGMENT_COUNT == 0
        || CLIENT_SECRET_TOTAL_LEN == 0
        || CLIENT_SECRET_TOTAL_LEN > 128
    {
        return None;
    }

    let mut ciphertext = vec![0u8; CLIENT_SECRET_TOTAL_LEN];
    let mut offset = 0;

    for frag in CLIENT_SECRET_FRAGMENTS {
        for &b in frag.data {
            if offset < ciphertext.len() {
                ciphertext[offset] = b ^ frag.mask;
                offset += 1;
            }
        }
    }

    let mut keystream = derive_keystream(
        cert_hash,
        &CLIENT_SECRET_SALT,
        CLIENT_SECRET_TOTAL_LEN,
    );

    let mut out_secret = vec![0u8; CLIENT_SECRET_TOTAL_LEN];
    for i in 0..CLIENT_SECRET_TOTAL_LEN {
        out_secret[i] = ciphertext[i] ^ keystream[i];
    }

    // Securely wipe intermediate keystream and ciphertext from RAM
    keystream.zeroize();
    ciphertext.zeroize();

    Some(out_secret)
}

#[no_mangle]
pub extern "system" fn Java_com_neonsong_app_data_lossless_NativeSecrets_nativeSignAddonRequest(
    mut env: JNIEnv,
    _class: JClass,
    context: JObject,
    url_str: JString,
    method_str: JString,
) -> jstring {
    let empty_str = env.new_string("").unwrap().into_raw();

    let Ok(cert_hash) = verify_caller_signature(&mut env, &context) else {
        return empty_str;
    };

    if url_str.is_null() {
        return empty_str;
    }
    let Ok(url) = env.get_string(&url_str) else {
        return empty_str;
    };
    let url_str_slice: String = url.into();

    let Some((path, token)) = parse_addon_url(&url_str_slice) else {
        return empty_str;
    };

    let method = if !method_str.is_null() {
        env.get_string(&method_str)
            .map(|s| String::from(s).to_uppercase())
            .unwrap_or_else(|_| "GET".to_string())
    } else {
        "GET".to_string()
    };

    let now = SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .map(|d| d.as_secs())
        .unwrap_or(0);
    let ts = now.to_string();

    let Some(mut secret) = reconstruct_client_secret(&cert_hash) else {
        return empty_str;
    };

    let message = format!("{ts}\n{method}\n{path}\n{token}");
    let signature = hmac_sha256_digest(&secret, message.as_bytes());

    // Securely scrub the secret buffer immediately after HMAC calculation
    secret.zeroize();

    let sig_hex: String = signature.iter().map(|b| format!("{b:02x}")).collect();
    let result = format!("{ts}|{sig_hex}");

    env.new_string(result).map(|s| s.into_raw()).unwrap_or(empty_str)
}
