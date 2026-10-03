package com.rumi.body_track_backend.storage;

/**
 * Result of a successful store.
 *
 * @param key         opaque, server-generated; never derived from the client filename
 * @param contentType detected from the file's magic bytes, not from the request
 * @param byteLength  size actually written
 */
public record StoredImage(String key, String contentType, long byteLength) {
}