## How to run:

Go to `appconfig.json`. Use public "imageId" to create AWS instance. It will automatically create a load balancer and
workers.

## Packages:

- utils: general functions useful to more than one package
- master: loadbalancer and autoscaler code
- worker: web server that solves requests
- EntryPoint: class that decides whether the instance will become a master or worker 