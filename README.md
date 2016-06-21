# jobs

## Production
One way to compile the project for production is using typesafe activator. Play will then use `application.conf` in order to setup the server:

    activator clean stage

Staged version can then be found in `target\universal\stage\bin\jobs`. You need to provide a secret in order to start the server `-Dplay.crypto.secret="changeme"`.

`application.conf` provides a possibility to set up HTTPS, where you can define the keystore to use. You can disable HTTP by providing `-Dhttp.port=disabled` to your arguments.

TEST JENKINS INTEGRATION
