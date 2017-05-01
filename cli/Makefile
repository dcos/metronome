all: env test binary

clean:
	bin/clean.sh

env:
	bin/env.sh

test: env
	bin/test.sh

packages:
	bin/packages.sh

binary: env
	pyinstaller binary/binary.spec

.PHONY: binary
