
# ENV
METADATA_HOME=/subject
METADATA_HOME=/metadata

replay_npe() {
  benchmark_name=$(basename $(pwd))
  benchmark_group=$(basename $(dirname $(pwd)))
  bash -c "$(jq -r ".commands.replay_npe" $METADATA_HOME/$benchmark_group/$benchmark_name/npetest.json)"
}

replay_manual_npe() {
  benchmark_name=$(basename $(pwd))
  benchmark_group=$(basename $(dirname $(pwd)))
  bash -c "$(jq -r ".commands.replay_manual_npe" $METADATA_HOME/$benchmark_group/$benchmark_name/npetest.json)"
}

