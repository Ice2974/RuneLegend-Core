execute unless data storage runelegend_test:player_probe active run return 0
$data modify storage runelegend_test:player_probe events append value {type:"revived",round:$(round)L,player_uuid:"$(player_uuid)",rescuer_uuid:"$(rescuer_uuid)",anchor:$(anchor)}
