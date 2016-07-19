# jobs

## Production
One way to compile the project for production is using typesafe activator. Play will then use `application.conf` in order to setup the server:

    activator clean stage

Staged version can then be found in `target\universal\stage\bin\jobs`. You need to provide a secret in order to start the server `-Dplay.crypto.secret="changeme"`.

`application.conf` provides a possibility to set up HTTPS, where you can define the keystore to use. You can disable HTTP by providing `-Dhttp.port=disabled` to your arguments.

## CLI
Metronome provides a DCOS CLI subcommands to interact with metronome via the DCOS CLI.   The project is under the `./cli` folder.  Details on how to use or test the subcommands are in it's [README.rst](cli/README.rst).
