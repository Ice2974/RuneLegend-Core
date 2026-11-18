execute unless data storage runelegend_test:round_probe active run return 0
$data modify storage runelegend_test:round_probe ended set value {round:$(round)L,reason:"$(reason)",outcome_id:"$(outcome_id)",identities:$(identities)}
scoreboard players add #ended rl_round_probe 1
execute store result score #alive_on_end rl_round_probe run runelegend query game alive_count
execute store result score #blocked_start rl_round_probe run runelegend game start
execute store success score #spawn_dimension rl_round_probe if dimension minecraft:overworld
execute store success score #has_executor rl_round_probe if entity @s
