execute unless data storage runelegend_test:round_probe active run return 0
$data modify storage runelegend_test:round_probe no_alive set value {round:$(round)L,identities:$(identities)}
scoreboard players add #no_alive rl_round_probe 1
execute if data storage runelegend_test:round_probe animate run schedule function runelegend_test:round/defeat 60t replace
data remove storage runelegend_test:round_probe animate
