#!/bin/bash
sudo docker build -t textimager-taxon-recognition .
sudo docker run -p 5001:5001 -it --rm --name taxtest textimager-taxon-recognition
