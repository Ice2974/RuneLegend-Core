execute unless data storage runelegend_test:player_probe active run return 0
$data modify storage runelegend_test:player_probe events append value {type:"downed",round:$(round)L,player_uuid:"$(player_uuid)",anchor:$(anchor)}
execute store result storage runelegend_test:player_probe blocked_finish int 1 run runelegend game finish runelegend_test:reentrant
execute store success storage runelegend_test:player_probe has_executor byte 1 if entity @s
execute store success storage runelegend_test:player_probe overworld_source byte 1 if dimension minecraft:overworld
