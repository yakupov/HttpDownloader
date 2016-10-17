package org.iyakupov.downloader.core.file.state;

/**
 * Defines whether the length of the downloadable part is known
 */
public enum FilePartLengthState {
    /**
     * We'll try to define the length when we'll know it
     */
    YET_UNKNOWN,

    /**
     * Length of this part is undefined - proceed with download until the stream won't end
     */
    UNKNOWN,

    /**
     * Length of this part is defined
     */
    KNOWN
}
