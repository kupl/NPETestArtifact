from datetime import datetime

GREEN='\033[0;32m'
RED='\033[0;31m'

INFO=f'{GREEN}[INFO]'
ERR=f'{RED}[ERR ]'
NC='\033[0m'

# Container directory name
RESULTS_HOME = '/experiment_results'

def get_time():
    return datetime.now().strftime("%m/%d-%H:%M:%S")


