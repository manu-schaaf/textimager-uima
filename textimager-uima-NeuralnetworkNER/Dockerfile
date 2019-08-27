FROM python:2

WORKDIR /usr/src/app

COPY requirements.txt ./
RUN pip install --no-cache-dir -r requirements.txt

COPY src/main/python/neuralnetwork_ner_rest ./
COPY src/main/python/neuralnetwork_ner_rest/models ./

EXPOSE 5000

ENV FLASK_APP neuralnetwork_ner_rest.py

CMD [ "python", "-m", "flask", "run", "-p", "5000", "--host", "0.0.0.0" ]
