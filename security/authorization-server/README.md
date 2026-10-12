# Authorization Server

## Component Responsibilities

| Component | Responsibility |
| --- | --- |
| **Authorization server / custom OIDC provider** | Stores client registrations, authenticates users, and issues tokens |
| **React app or gateway handling login** | Acts as the OAuth client and requests tokens |
| **API gateway** | Intercepts API requests and routes them after authorization |
| **Authorizer** | Validates the access token and checks permission |
| **Product/profile services** | Handle the business request |
