# Llamatik Server

Lightweight llama.cpp HTTP server

## How to Run this service

```
./gradlew run
```

## How to run it using Docker

```
docker build -t llamatik .
docker run -p 8080:8080 llamatik
```

To run Llamatik container as a service on Linux with systemd:﻿
Create a service descriptor file /etc/systemd/system/docker.llamatik.service:

```
[Unit]
Description=Llamatik
After=docker.service
Requires=docker.service

[Service]
TimeoutStartSec=0
Restart=always
ExecStartPre=-/usr/bin/docker exec %n stop
ExecStartPre=-/usr/bin/docker rm %n
ExecStart=/usr/bin/docker run -p 8080:8080 llamatik

[Install]
WantedBy=default.target
```

Enable starting the service on system boot with the following command:

```
sudo systemctl enable docker.llamatik
```

You can also stop and start the service manually at any moment with the following commands,
respectively:

```
sudo service docker.llamatik stop
sudo service docker.llamatik start
```
