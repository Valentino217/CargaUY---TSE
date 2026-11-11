FROM python:3.11-alpine
WORKDIR /app
ENV PORT=8080 \
    PYTHONUNBUFFERED=1
COPY mock_tracking.py /app/
EXPOSE 8080
HEALTHCHECK --interval=10s --timeout=3s --start-period=3s --retries=3 \
  CMD python -c "import os, urllib.request; urllib.request.urlopen('http://127.0.0.1:' + os.environ.get('PORT', '8080') + '/health', timeout=2)"
CMD ["python", "mock_tracking.py"]
