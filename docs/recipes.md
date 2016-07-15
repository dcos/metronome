---
title: Metronome Recipes
---

# Metronome Recipes


### Simple Example

```json
{
  "id": "simple",
  "description": "Hello World Example",
  "run": {
    "cpus": 0.1,
    "mem": 32,
    "disk": 0,
    "cmd": "date >> /tmp/hello",
    "restart": {
      "policy": "NEVER",
      "activeDeadlineSeconds": 120
    }
  }
}
```

