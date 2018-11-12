---
title: Universal Container Runtime (UCR) Run Configurations
---

# UCR

It is now possible to run jobs with Docker containers using [Universal Container Runtime](http://mesos.apache.org/documentation/latest/container-image/).

**Important:** Note that UCR could support various image types, therefore the `ucr.image` property is 
an _object_ instead of a _string_ as with `docker`. Specifying just `"image": { "id": "ubuntu" }` will 
default to `docker` image type.

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

Note that specifying both `ucr` and `docker` is invalid and will lead to a validation error.