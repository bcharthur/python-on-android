import requests
import certifi

def test_ssl():
    try:
        response = requests.get("https://www.google.com", verify=certifi.where())
        return f"SSL test réussi: {response.status_code}"
    except Exception as e:
        return f"Test SSL échoué: {e}"

# Vous pouvez appeler cette fonction depuis votre activité principale pour vérifier
