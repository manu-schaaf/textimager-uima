#!/bin/bash
sudo docker build -t textimager-neuralnetwork-ner .
sudo docker run -p 5000:5000 -it --rm --name nertest textimager-neuralnetwork-ner
