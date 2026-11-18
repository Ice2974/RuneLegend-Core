execute unless data storage runelegend_test:round_probe active run return 0
$data modify storage runelegend_test:round_probe started set value {round:$(round)L,identities:$(identities)}
scoreboard players add #started rl_round_probe 1
execute store result score #alive_on_start rl_round_probe run runelegend query game alive_count
execute store result score #blocked_finish rl_round_probe run runelegend game finish runelegend_test:reentrant
