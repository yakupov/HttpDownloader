# HttpDownloader
## HttpDownloaderCore
This is a java library that manages multi-threaded downloads. All download requests should be submitted and managed via an instance of IDispatchingQueue.
The Dispatching Queue allows its user to submit, cancel, resume and get the list of existing file download requests. It also allows to set the maximal number of worker threads (i.e. threads that perform downloading). The thread pool is built into the Queue.
## HttpDownloaderApp
This is a simple JavaFX GUI app for the HttpDownloaderCore library. It's being built as a fat executable JAR.
