function runelegend_test:restart/reset
$data modify storage runelegend_test:restart active set value {round:$(round)L}
data modify storage runelegend_test:restart pending set from storage runelegend_test:restart active
scoreboard players set #phase rl_restart_phase 1
schedule function runelegend_test:restart/wave_due 100t replace
