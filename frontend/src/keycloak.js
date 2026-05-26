import Keycloak from 'keycloak-js'
import { KEYCLOAK_BASE, REALM, CLIENT_ID } from './constants'

const keycloak = new Keycloak({
  url: KEYCLOAK_BASE,
  realm: REALM,
  clientId: CLIENT_ID,
})

export default keycloak
