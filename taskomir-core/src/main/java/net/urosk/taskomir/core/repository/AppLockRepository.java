package net.urosk.taskomir.core.repository;
import net.urosk.taskomir.core.domain.AppLock;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface AppLockRepository extends MongoRepository<AppLock, String> {
    // Lahko uporabljaš privzete metode findById, insert, deleteById, itd.
}