# vim:set ft=dockerfile:
ARG BASE_IMAGE

FROM ${BASE_IMAGE}

ARG VERSION
ARG SCM_REF
ARG BUILD_TAG
ARG CLID

LABEL org.nuxeo.sample.version=${VERSION}
LABEL org.nuxeo.sample.scm-ref=${SCM_REF}
LABEL org.nuxeo.sample.build-tag=${BUILD_TAG}

COPY --chown=nuxeo:0 target/nuxeo-sample-final1-package-*.zip /home/nuxeo/local-packages/nuxeo-sample-final1-package.zip

USER root
# install local package with a clid, to fetch dependencies from Connect
RUN /install-packages.sh --clid ${CLID} /home/nuxeo/local-packages/nuxeo-sample-final1-package.zip
RUN chown -R 900:0 ${NUXEO_HOME} \
  && chmod -R g+rwX ${NUXEO_HOME}
USER 900
