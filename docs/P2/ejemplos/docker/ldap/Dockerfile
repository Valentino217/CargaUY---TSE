FROM debian:bookworm-slim
RUN apt-get update && DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends slapd ldap-utils ca-certificates && rm -rf /var/lib/apt/lists/*
COPY iniciar.sh /iniciar.sh
RUN chmod 755 /iniciar.sh
EXPOSE 1389
ENTRYPOINT ["/bin/sh", "/iniciar.sh"]
