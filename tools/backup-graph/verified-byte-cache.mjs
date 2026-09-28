import { createHash } from 'node:crypto';

/** Acceleration only: the caller must provide freshly fetched, ownership-checked Drive metadata. */
export class VerifiedByteCache {
  constructor(maxBytes = 4 * 1024 * 1024) { this.maxBytes = maxBytes; this.bytes = 0; this.entries = new Map(); }
  key(context, id) { return JSON.stringify([context.account, context.root, context.lineage, id]); }
  get(context, metadata) {
    const key = this.key(context, metadata.id), entry = this.entries.get(key);
    if (!entry) return null;
    if (!/^[a-f0-9]{64}$/.test(metadata.sha256Checksum || '') || metadata.sha256Checksum !== entry.sha256 ||
        metadata.appProperties?.sha256 !== entry.sha256 || Number(metadata.size) !== entry.bytes.length) {
      this.entries.delete(key); this.bytes -= entry.bytes.length; return null;
    }
    this.entries.delete(key); this.entries.set(key, entry);
    return Buffer.from(entry.bytes);
  }
  put(context, metadata, bytes) {
    const digest = createHash('sha256').update(bytes).digest('hex');
    if (bytes.length !== Number(metadata.size) || digest !== metadata.appProperties?.sha256 ||
        (metadata.sha256Checksum && metadata.sha256Checksum !== digest)) throw Error('Remote byte verification failed');
    if (metadata.sha256Checksum !== digest || bytes.length > this.maxBytes) return;
    const key = this.key(context, metadata.id), previous = this.entries.get(key);
    if (previous) { this.bytes -= previous.bytes.length; this.entries.delete(key); }
    while (this.bytes + bytes.length > this.maxBytes) {
      const oldest = this.entries.keys().next().value;
      this.bytes -= this.entries.get(oldest).bytes.length; this.entries.delete(oldest);
    }
    this.entries.set(key, { bytes: Buffer.from(bytes), sha256: digest }); this.bytes += bytes.length;
  }
  clear() { this.entries.clear(); this.bytes = 0; }
}
