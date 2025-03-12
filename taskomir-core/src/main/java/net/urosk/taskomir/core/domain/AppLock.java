package net.urosk.taskomir.core.domain;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Represents the "PRIMARY" lock in MongoDB.
 * When one instance becomes primary, it inserts a document with "_id = PRIMARY"
 * into the "app_locks" collection, along with relevant Taskomir settings.
 */
@Document(collection = "app_locks")
@Getter
@Setter
public class AppLock {

    @Id
    private String name;              // "PRIMARY"
    private String instanceId;           // e.g. hostname or unique ID
    private long lockedAt;            // timestamp when the lock was acquired

    // Store the Taskomir settings that the primary actually uses:
    private long cleanupIntervalMs;
    private long succeededRetentionMs;
    private long deletedRetentionMs;
    private int poolSize;
    private int queueCapacity;


}