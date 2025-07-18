# Multiplatform Kickstarter Service Template

Basic Service template in ktor

## How to Run this service

```
./gradlew run
```

## How to run it using Docker

```
docker build -t multiplatformkickstarter .
docker run -p 8080:8080 multiplatformkickstarter
```

To run MK Service container as a service on Linux with systemd:﻿
Create a service descriptor file /etc/systemd/system/docker.multiplatformkickstarter.service:

```
[Unit]
Description=MK Service
After=docker.service
Requires=docker.service

[Service]
TimeoutStartSec=0
Restart=always
ExecStartPre=-/usr/bin/docker exec %n stop
ExecStartPre=-/usr/bin/docker rm %n
ExecStart=/usr/bin/docker run -p 8080:8080 multiplatformkickstarter

[Install]
WantedBy=default.target
```

Enable starting the service on system boot with the following command:

```
sudo systemctl enable docker.multiplatformkickstarter
```

You can also stop and start the service manually at any moment with the following commands,
respectively:

```
sudo service docker.multiplatformkickstarter stop
sudo service docker.multiplatformkickstarter start
```
