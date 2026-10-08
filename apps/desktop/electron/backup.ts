import {
  randomBytes,
  pbkdf2Sync,
  createCipheriv,
  createDecipheriv,
} from "node:crypto";
export function encodeBackup(value: unknown, password = "") {
  if (!password) return JSON.stringify(value);
  if (password.length < 8)
    throw Error("Use uma senha de backup com pelo menos 8 caracteres.");
  const salt = randomBytes(16),
    iv = randomBytes(12),
    key = pbkdf2Sync(password, salt, 210000, 32, "sha256");
  const cipher = createCipheriv("aes-256-gcm", key, iv);
  const bytes = Buffer.concat([
    cipher.update(JSON.stringify(value), "utf8"),
    cipher.final(),
    cipher.getAuthTag(),
  ]);
  return JSON.stringify({
    encrypted: 1,
    salt: salt.toString("base64"),
    iv: iv.toString("base64"),
    data: bytes.toString("base64"),
  });
}
export function decodeBackup(text: string, password = "") {
  const value = JSON.parse(text);
  if (value.encrypted !== 1) return value;
  if (!password) throw Error("Informe a senha desse backup antes de importar.");
  try {
    const salt = Buffer.from(value.salt, "base64"),
      iv = Buffer.from(value.iv, "base64"),
      bytes = Buffer.from(value.data, "base64");
    if (salt.length !== 16 || iv.length !== 12 || bytes.length < 17)
      throw Error("Formato inválido.");
    const key = pbkdf2Sync(password, salt, 210000, 32, "sha256");
    const cipher = createDecipheriv("aes-256-gcm", key, iv);
    cipher.setAuthTag(bytes.subarray(-16));
    return JSON.parse(
      Buffer.concat([
        cipher.update(bytes.subarray(0, -16)),
        cipher.final(),
      ]).toString("utf8"),
    );
  } catch {
    throw Error(
      "Senha incorreta ou backup danificado. Nenhum registro foi alterado.",
    );
  }
}
