package org.iyakupov.downloader.core.file.state;

/**
 * Statuses of a file download request.
 */
public enum FileDownloadState {
    /**
     * A new file download request. File part download requests are not yet created.
     */
    INITIATED {
        public int getPriority() { return 0; }
        public FileDownloadState onError() { return FAILED; }
        public FileDownloadState onSuspendRequested() { return PAUSING; }
        public FileDownloadState onPauseRequested() { return PAUSING; }
        public FileDownloadState onPaused() { return PAUSED; }
        public FileDownloadState onDownloading() { return DOWNLOADING; }
        public FileDownloadState onCancelled() { return CANCELLED; }
        public FileDownloadState onCompleted() { return UNSAVED; }
        public FileDownloadState onPending() { return PENDING; }
    },

    /**
     * All known file parts are downloaded, but the file was not saved
     */
    UNSAVED {
        public int getPriority() { return 5; }
        public FileDownloadState onError() { return FAILED; }
        public FileDownloadState onSuspendRequested() { return PAUSING; }
        public FileDownloadState onPauseRequested() { return PAUSING; }
        public FileDownloadState onPaused() { return PAUSED; }
        public FileDownloadState onDownloading() { return DOWNLOADING; }
        public FileDownloadState onCancelled() { return CANCELLED; }
        public FileDownloadState onCompleted() { return UNSAVED; }
        public FileDownloadState onPending() { return PENDING; }
    },

    /**
     * Parts of this file are queued for download
     */
    PENDING {
        public int getPriority() { return 10; }
        public FileDownloadState onError() { return FAILED; }
        public FileDownloadState onSuspendRequested() { return PAUSING; }
        public FileDownloadState onPauseRequested() { return PAUSING; }
        public FileDownloadState onPaused() { return PAUSED; }
        public FileDownloadState onDownloading() { return DOWNLOADING; }
        public FileDownloadState onCancelled() { return CANCELLED; }
        public FileDownloadState onCompleted() { return PENDING; }
        public FileDownloadState onPending() { return PENDING; }
    },

    /**
     * Some parts of this file are currently downloading
     */
    DOWNLOADING {
        public int getPriority() { return 20; }
        public FileDownloadState onError() { return FAILED; }
        public FileDownloadState onSuspendRequested() { return PAUSING; }
        public FileDownloadState onPauseRequested() { return PAUSING; }
        public FileDownloadState onPaused() { return PAUSING; }
        public FileDownloadState onDownloading() { return DOWNLOADING; }
        public FileDownloadState onCancelled() { return CANCELLED; }
        public FileDownloadState onCompleted() { return DOWNLOADING; }
        public FileDownloadState onPending() { return DOWNLOADING; }
    },

    /**
     * File download is paused (all parts are on halt)
     */
    PAUSED {
        public int getPriority() { return 30; }
        public FileDownloadState onError() { return FAILED; }
        public FileDownloadState onSuspendRequested() { return PAUSING; }
        public FileDownloadState onPauseRequested() { return PAUSING; }
        public FileDownloadState onPaused() { return PAUSED; }
        public FileDownloadState onDownloading() { return PAUSING; }
        public FileDownloadState onCancelled() { return CANCELLED; }
        public FileDownloadState onCompleted() { return PAUSED; }
        public FileDownloadState onPending() { return PAUSED; }
    },

    /**
     * File download pause was requested
     */
    PAUSING {
        public int getPriority() { return 50; }
        public FileDownloadState onError() { return FAILED; }
        public FileDownloadState onSuspendRequested() { return PAUSING; }
        public FileDownloadState onPauseRequested() { return PAUSING; }
        public FileDownloadState onPaused() { return PAUSING; }
        public FileDownloadState onDownloading() { return PAUSING; }
        public FileDownloadState onCancelled() { return CANCELLED; }
        public FileDownloadState onCompleted() { return PAUSING; }
        public FileDownloadState onPending() { return PAUSING; }
    },

    /**
     * There is an error with at least one of this file's parts
     */
    FAILED {
        public int getPriority() { return 100; }
        public FileDownloadState onError() { return FAILED; }
        public FileDownloadState onSuspendRequested() { return FAILED; }
        public FileDownloadState onPauseRequested() { return FAILED; }
        public FileDownloadState onPaused() { return FAILED; }
        public FileDownloadState onDownloading() { return FAILED; }
        public FileDownloadState onCancelled() { return FAILED; }
        public FileDownloadState onCompleted() { return FAILED; }
        public FileDownloadState onPending() { return FAILED; }
    },

    /**
     * The download is cancelled
     */
    CANCELLED {
        public int getPriority() { return 150; }
        public FileDownloadState onError() { return CANCELLED; }
        public FileDownloadState onSuspendRequested() { return CANCELLED; }
        public FileDownloadState onPauseRequested() { return CANCELLED; }
        public FileDownloadState onPaused() { return CANCELLED; }
        public FileDownloadState onDownloading() { return CANCELLED; }
        public FileDownloadState onCancelled() { return CANCELLED; }
        public FileDownloadState onCompleted() { return CANCELLED; }
        public FileDownloadState onPending() { return CANCELLED; }
    },

    /**
     * Download is complete, the file was saved
     */
    DONE {
        public int getPriority() { return 500; }
        public FileDownloadState onError() { return DONE; }
        public FileDownloadState onSuspendRequested() { return DONE; }
        public FileDownloadState onPauseRequested() { return DONE; }
        public FileDownloadState onPaused() { return DONE; }
        public FileDownloadState onDownloading() { return DONE; }
        public FileDownloadState onCancelled() { return DONE; }
        public FileDownloadState onCompleted() { return DONE; }
        public FileDownloadState onPending() { return DONE; }
    };

    /**
     * Priority matters when we have multiple {@link FileDownloadState}, which were obtained after processing
     * of different sets of {@link FilePartDownloadState}. The "total" state of the file will be the state with
     * the highest priority.
     *
     * @return Numeric value of this state's priority
     */
    public abstract int getPriority();

    /**
     * @return Go from the current state to the next state if a file part with the status
     * of {@link FilePartDownloadState#FAILED} was found
     */
    public abstract FileDownloadState onError();

    /**
     * @return Go from the current state to the next state if a file part with the status
     * of {@link FilePartDownloadState#SUSPEND_REQUESTED} was found
     */
    public abstract FileDownloadState onSuspendRequested();

    /**
     * @return Go from the current state to the next state if a file part with the status
     * of {@link FilePartDownloadState#PAUSE_REQUESTED} was found
     */
    public abstract FileDownloadState onPauseRequested();

    /**
     * @return Go from the current state to the next state if a file part with the status
     * of {@link FilePartDownloadState#PAUSED} was found
     */
    public abstract FileDownloadState onPaused();

    /**
     * @return Go from the current state to the next state if a file part with the status
     * of {@link FilePartDownloadState#DOWNLOADING} was found
     */
    public abstract FileDownloadState onDownloading();

    /**
     * @return Go from the current state to the next state if a file part with the status
     * of {@link FilePartDownloadState#CANCELLED} was found
     */
    public abstract FileDownloadState onCancelled();

    /**
     * @return Go from the current state to the next state if a file part with the status
     * of {@link FilePartDownloadState#DONE} was found
     */
    public abstract FileDownloadState onCompleted();

    /**
     * @return Go from the current state to the next state if a file part with the status
     * of {@link FilePartDownloadState#PENDING} was found
     */
    public abstract FileDownloadState onPending();
}
