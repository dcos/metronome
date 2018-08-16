define USAGE
Metronome system integration tests ⚙️

Commands:
  init      Install Python dependencies with pipenv
  build     Run formatter and linter.
  test      Test system integration. The DCOS_URL environment variable must be
            present.
endef

export USAGE
help:
	@echo "$$USAGE"

init:
	pip3 install pipenv
	pipenv sync

build:
	pipenv run flake8 --count --max-line-length=120 system

test:
	@while [[ "$(curl -s -o $(DCOS_URL) -w ''%{http_code}'' localhost:9000)" != "200" ]]; do sleep 5; done

	pipenv run shakedown \
      --stdout all \
      --stdout-inline \
      --ssl-no-verify \
      --timeout 360000 \
      --pytest-option "--junitxml=../../shakedown.xml" \
      --pytest-option --verbose \
      --pytest-option --full-trace \
      --ssh-key-file "$(CLI_TEST_SSH_KEY)" \
      --ssh-user "centos" \
      --dcos-url "$(DCOS_URL)" test_root_metronome.py
