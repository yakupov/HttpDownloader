package org.iyakupov.downloader.core.file.state;

/**
 * The status of a file part download request
 */
public enum FilePartDownloadState {
    /**
     * Ready to proceed with download
     */
    PENDING {
        public FilePartDownloadState onError() { return FAILED; }
        public FilePartDownloadState onSuspendRequest() { return PENDING; }
        public FilePartDownloadState onPauseRequest() { return PAUSED; }
        public FilePartDownloadState onPauseConfirm() { return PAUSED; }
        public FilePartDownloadState onResume() { return PENDING; }
        public FilePartDownloadState onCancel() { return CANCELLED; }
        public FilePartDownloadState onStarted() { return DOWNLOADING; }
        public FilePartDownloadState onCompleted() {
            throw new IllegalStateException("Cannot complete a pending task");
        }
    },

    /**
     * Download in progress
     */
    DOWNLOADING {
        public FilePartDownloadState onError() { return FAILED; }
        public FilePartDownloadState onSuspendRequest() { return SUSPEND_REQUESTED; }
        public FilePartDownloadState onPauseRequest() { return PAUSE_REQUESTED; }
        public FilePartDownloadState onPauseConfirm() {
            throw new IllegalStateException("Cannot suspend downloading task immediately. Need to submit suspend request first.");
        }
        public FilePartDownloadState onResume() { return DOWNLOADING; }
        public FilePartDownloadState onCancel() { return CANCELLED; }
        public FilePartDownloadState onStarted() { return DOWNLOADING; }
        public FilePartDownloadState onCompleted() { return DONE; }
    },

    /**
     * Download in progress, but soon to be paused
     */
    PAUSE_REQUESTED {
        public FilePartDownloadState onError() { return FAILED; }
        public FilePartDownloadState onSuspendRequest() { return PAUSE_REQUESTED; }
        public FilePartDownloadState onPauseRequest() { return PAUSE_REQUESTED; }
        public FilePartDownloadState onPauseConfirm() { return PAUSED; }
        public FilePartDownloadState onResume() { return PAUSE_REQUESTED; }
        public FilePartDownloadState onCancel() { return CANCELLED; }
        public FilePartDownloadState onStarted() { return PAUSE_REQUESTED; }
        public FilePartDownloadState onCompleted() { return DONE; }
    },

    /**
     * Download is paused, but may be resumed later. The data on disk is expected to be intact.
     */
    PAUSED {
        public FilePartDownloadState onError() { return FAILED; }
        public FilePartDownloadState onSuspendRequest() { return PAUSED; }
        public FilePartDownloadState onPauseRequest() { return PAUSED; }
        public FilePartDownloadState onPauseConfirm() { return PAUSED; }
        public FilePartDownloadState onResume() { return PENDING; }
        public FilePartDownloadState onCancel() { return CANCELLED; }
        public FilePartDownloadState onStarted() {
            throw new IllegalStateException("Download may not start for a paused task");
        }
        public FilePartDownloadState onCompleted() {
            throw new IllegalStateException("Cannot complete a paused task");
        }
    },

    /**
     * Download is forcefully paused and should be queued again
     */
    SUSPEND_REQUESTED {
        public FilePartDownloadState onError() { return FAILED; }
        public FilePartDownloadState onSuspendRequest() { return SUSPEND_REQUESTED; }
        public FilePartDownloadState onPauseRequest() { return SUSPEND_REQUESTED; }
        public FilePartDownloadState onPauseConfirm() { return PAUSED; }
        public FilePartDownloadState onResume() { return SUSPEND_REQUESTED; }
        public FilePartDownloadState onCancel() { return CANCELLED; }
        public FilePartDownloadState onStarted() { return SUSPEND_REQUESTED; }
        public FilePartDownloadState onCompleted() { return DONE; }
    },

    /**
     * Download is cancelled and cannot be resumed
     */
    CANCELLED {
        public FilePartDownloadState onError() { return CANCELLED; }
        public FilePartDownloadState onSuspendRequest() { return CANCELLED; }
        public FilePartDownloadState onPauseRequest() { return CANCELLED; }
        public FilePartDownloadState onPauseConfirm() { return CANCELLED; }
        public FilePartDownloadState onResume() { return CANCELLED; }
        public FilePartDownloadState onCancel() { return CANCELLED; }
        public FilePartDownloadState onStarted() { return CANCELLED; }
        public FilePartDownloadState onCompleted() { return CANCELLED; }
    },

    /**
     * Error has happened, but the user can try to restart the download
     */
    FAILED {
        public FilePartDownloadState onError() { return FAILED; }
        public FilePartDownloadState onSuspendRequest() { return FAILED; }
        public FilePartDownloadState onPauseRequest() { return FAILED; }
        public FilePartDownloadState onPauseConfirm() { return FAILED; }
        public FilePartDownloadState onResume() { return PENDING; }
        public FilePartDownloadState onCancel() { return CANCELLED; }
        public FilePartDownloadState onStarted() {
            throw new IllegalStateException("Download may not start for a failed task");
        }
        public FilePartDownloadState onCompleted() {
            throw new IllegalStateException("Cannot complete a failed task");
        }
    },

    /**
     * The download is complete
     */
    DONE {
        public FilePartDownloadState onError() { return FAILED; }
        public FilePartDownloadState onSuspendRequest() { return DONE; }
        public FilePartDownloadState onPauseRequest() { return DONE; }
        public FilePartDownloadState onPauseConfirm() { return DONE; }
        public FilePartDownloadState onResume() { return DONE; }
        public FilePartDownloadState onCancel() { return CANCELLED; }
        public FilePartDownloadState onStarted() { return DONE; }
        public FilePartDownloadState onCompleted() { return DONE; }
    };

    /**
     * @return Go from the current state to the next state in case of error
     */
    public abstract FilePartDownloadState onError();

    /**
     * @return Go from the current state to the next state in case download forced pause (suspend) request
     */
    public abstract FilePartDownloadState onSuspendRequest();

    /**
     * @return Go from the current state to the next state in case download pause request
     */
    public abstract FilePartDownloadState onPauseRequest();

    /**
     * @return Go from the current state to the next state in case of pause confirmation
     */
    public abstract FilePartDownloadState onPauseConfirm();

    /**
     * @return Go from the current state to the next state in case of download resume request
     */
    public abstract FilePartDownloadState onResume();

    /**
     * @return Go from the current state to the next state in case of download cancellation
     */
    public abstract FilePartDownloadState onCancel();

    /**
     * @return Go from the current state to the next state in case of download start confirmation
     */
    public abstract FilePartDownloadState onStarted();

    /**
     * @return Go from the current state to the next state in case of download completion confirmation
     */
    public abstract FilePartDownloadState onCompleted();
}
