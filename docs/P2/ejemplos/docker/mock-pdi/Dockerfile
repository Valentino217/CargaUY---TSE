FROM python:3.12-slim
WORKDIR /app
COPY mock_pdi.py pdi.wsdl ./
EXPOSE 8080
CMD ["python", "-u", "mock_pdi.py"]
