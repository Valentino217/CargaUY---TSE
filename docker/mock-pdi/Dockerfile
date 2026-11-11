FROM python:3.12-slim
WORKDIR /app
RUN pip install --no-cache-dir --progress-bar off 'psycopg[binary]==3.2.13'
COPY mock_pdi.py pdi.wsdl ./
EXPOSE 8080
CMD ["python", "-u", "mock_pdi.py"]
