---
title: Universal Container Runtime (UCR) Run Configurations
---

# UCR

It is now possible to run jobs with Docker containers using [Universal Container Runtime](http://mesos.apache.org/documentation/latest/container-image/).

To enable UCR, the run configuration needs to contain the `ucr` section:

```
{
  "description": "example ucr specification",
  "id": "ucr-docker",
  "run": {
    "cmd": "sleep inf",
    "cpus": 0.2,
    "mem": 32,
    "ucr": {
      "image":  {
        "id": "ubuntu",
        "forcePull": true
      }
    }
  }
}
```

Please note that UCR uses a differnt syntax when defining docker image section. Also, specifying both `docker` and `ucr` will lead to a validation error.