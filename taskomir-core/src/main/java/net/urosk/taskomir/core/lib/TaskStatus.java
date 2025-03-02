package net.urosk.taskomir.core.lib;

public enum TaskStatus {
    ENQUEUED,    // Čakalno stanje
    SCHEDULED,   // Načrtovano zagon
    PROCESSING,  // V teku
    SUCCEEDED,   // Zaključen uspešno
    FAILED,      // Zaključen z napako
    DELETED      // Označeno kot izbrisano
}