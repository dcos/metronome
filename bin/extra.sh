# The content of this file will be added to the metronome start script

if [ -n "${TLS_TRUSTSTORE-}" ]; then
    echo "add trust store: -Djavax.net.ssl.trustStore=$TLS_TRUSTSTORE"
    addJava "-Djavax.net.ssl.trustStore=$TLS_TRUSTSTORE"
fi

if [ -n "${JVM_SECURITY_PROPERTIES_FILE_PATH-}" ]; then
    echo "add -Djava.security.properties=${JVM_SECURITY_PROPERTIES_FILE_PATH}"
    addJava "-Djava.security.properties=${JVM_SECURITY_PROPERTIES_FILE_PATH}"
fi

# Define default thread pool size
# Make sure that we have more than one thread -- otherwise some unmarked blocking operations might cause trouble.
#
# See [The Global Execution Context](http://docs.scala-lang.org/overviews/core/futures.html#the-global-execution-context)
# in the scala documentation.
#
# Here is the relevant excerpt in case the link gets broken:
#
# The Global Execution Context
#
# ExecutionContext.global is an ExecutionContext backed by a ForkJoinPool. It should be sufficient for most
# situations but requires some care. A ForkJoinPool manages a limited amount of threads (the maximum amount of
# thread being referred to as parallelism level). The number of concurrently blocking computations can exceed the
# parallelism level only if each blocking call is wrapped inside a blocking call (more on that below). Otherwise,
# there is a risk that the thread pool in the global execution context is starved, and no computation can proceed.
#
# By default the ExecutionContext.global sets the parallelism level of its underlying fork-join pool to the amount
# of available processors (Runtime.availableProcessors). This configuration can be overriden by setting one
# (or more) of the following VM attributes:
#
# scala.concurrent.context.minThreads - defaults to 5 threads
# scala.concurrent.context.numThreads - can be a number or a multiplier (N) in the form ‘xN’ ;
#                                       defaults to the double of available processors
# scala.concurrent.context.maxThreads - defaults to 64 threads
#
# The parallelism level will be set to numThreads as long as it remains within [minThreads; maxThreads].
#
# As stated above the ForkJoinPool can increase the amount of threads beyond its parallelismLevel in the presence of blocking computation.
addJava "-Dscala.concurrent.context.minThreads=${METRONOME_THREADPOOL_MINTHREADS:-5}"
addJava "-Dscala.concurrent.context.numThreads=${METRONOME_THREADPOOL_NUMTHREADS:-x2}"
addJava "-Dscala.concurrent.context.maxThreads=${METRONOME_THREADPOOL_MAXTHREADS:-64}"

