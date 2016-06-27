
# The content of this file will be added to the metronome start script

if [ -n "${TLS_TRUSTSTORE-}" ]; then
    echo "add trust store: -Djavax.net.ssl.trustStore=$TLS_TRUSTSTORE"
    addJava "-Djavax.net.ssl.trustStore=$TLS_TRUSTSTORE"
fi

