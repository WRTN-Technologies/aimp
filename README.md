# AIMP

---

## Prerequisites

```
python >= 3.11
```

## Build & Deployment

```shell script
# Build jars
make all
```

```shell script
# Install the Pulumi CLI
brew install pulumi/tap/pulumi

# Go to deploy/pulumi directory
cd deploy/pulumi

# Install packages
python3 -m venv venv
venv/bin/pip install -r requirements.txt

# Create one of the Pulumi stacks (test, dev, prod)
pulumi stack init

# Check your pulumi access tokens
https://app.pulumi.com/account/tokens

# Select your stack
pulumi stack select dev

# Deploy your stack
pulumi up
```

## Documentation

See [AIMP Documentation](https://wrtntechnologies.mintlify.app/) for User Guide and API Reference.
