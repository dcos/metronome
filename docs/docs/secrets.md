# Secrets

Metronome has a pluggable interface for secret store providers. The secrets API provides a way for applications to consume sensitive data without exposing that data directly via API objects. For example, you can use a secret to securely provide a database password that is needed by a service or pod without embedding the password itself into the Metronome app or pod JSON.

Secrets are an opt-in feature in Metronome. Enable them by specifying `secrets` with the `METRONOME_FEATURES_ENABLE` environmental variable.

Metronome does not ship with a default secrets plugin implementation out-of-the-box. If you enable the secrets feature without providing and configuring a plugin, Metronome will consume API objects that use secrets, but no secrets will actually be passed to apps/pods at launch time.

Internally, Metronome relies on Marathon to work with secrets plugins implementations. Marathon plugins are configured using the `METRONOME_PLUGIN_DIR` and `METRONOME_PLUGIN_CONF` environmental variables. For further information regarding plugin development and configuration see [Extend Marathon with Plugins](https://mesosphere.github.io/marathon/docs/plugin.html).

**Important:** Metronome will only provide the API to configure and store these secrets. You need to write and register a Marathon plugin that interprets these secrets.

There are two ways to consume secrets in a job definition: as either an environment variable or a container volume (a file-based secret).

## Environment variable-based secrets

The environment API allows you to reference a secret as the value of an environment variable.

In the example below, the secret is under the environment variable "SECRET_ENV". Observe how the "env" and "secrets" objects are used to define environment variable-based secrets.

```json
{
  "id": "job-env-secret",
  "description": "job example with env based secrets",
  "labels": {},
  "run": {
    "cpus": 0.01,
    "mem": 32,
    "disk": 0,
    "cmd": "echo $SECRET_ENV >> $MESOS_SANDBOX/secret-env; sleep 5",
    "env": {
      "SECRET_ENV": {
        "secret": "secret1"
      }
    },
    "secrets": {
      "secret1": {
        "source": "/mysecret"
      }
    }
  }
}
```

## File based secrets

The file-based secret API allows you to reference a secret as a file at a particular path in a container’s file system. File-based secrets are available in the sandbox of the task ($MESOS_SANDBOX/<configured-path>). **Important:**: File-based secrets are only supported by UCR.

In the example below, the secret will have the filename /mnt/test and will be available in the task’s sandbox ($MESOS_SANDBOX/mnt/test).

```json
{
  "id": "job-fbs",
  "description": "job example with file based secrets",
  "run": {
    "cpus": 0.01,
    "mem": 32,
    "disk": 0,
    "cmd": "echo $MESOS_SANDBOX/mnt/test; sleep 5",
    "secrets": {
      "secret1": {
        "source": "/mysecret"
      }
    },
    "volumes": [
         {
           "containerPath": "/mnt/test",
           "secret": "secret1"
         }
    ],
    "ucr": {
      "image": { "id": "ubuntu"}
    }
  }
}
```