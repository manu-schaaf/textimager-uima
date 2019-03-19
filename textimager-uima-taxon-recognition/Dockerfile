FROM python:2

WORKDIR /usr/src/app

COPY requirements.txt ./
RUN pip install --no-cache-dir -r requirements.txt

COPY src/main/python/taxon_recognition_rest ./

EXPOSE 5001

ENV FLASK_APP taxon_recognition_rest.py

CMD [ "python", "-m", "flask", "run", "-p", "5001", "--host", "0.0.0.0" ]
