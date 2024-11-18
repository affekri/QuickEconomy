<div align="center">

# QuickEconomy

A lightweight economy plugin for Minecraft servers running PaperMC.  
**Update 1.1 is live!**  
[Upgrade guide](https://docs.derfla.net/quickeconomy/usage/upgrade/to_1_1/)

[![GitHub issues](https://img.shields.io/github/issues-raw/affekri/QuickEconomy)](https://github.com/affekri/QuickEconomy/issues)
[![Modrinth Downloads](https://img.shields.io/modrinth/dt/TT2OA0w5)](https://modrinth.com/plugin/quickeconomy/versions)
[![Modrinth Version](https://img.shields.io/modrinth/v/TT2OA0w5)](https://modrinth.com/plugin/quickeconomy/versions)

[![QuickEconomy Stats](https://bstats.org/signatures/bukkit/QuickEconomy.svg)](https://bstats.org/plugin/bukkit/QuickEconomy/20985)

</div>

## Commands  

Commands featured in this plugin:  

### Balance  

The /balance command shows the player's balance.  
Using subcommand send, players can send coins to other players.  
Players who are server operators can use subcommand set, add och subtract to  
modify the balance of themselves or any other specified player. Or do "/bal player" to  
see any players balance.  

### Bal  

The /bal command has the same functionality as /balance.  

### QuickEconomy  

The /quickeconomy command displays helpful information about the plugin.

## Signs  

This plugin modifies the behavior of the different signs.  

### Bank  

As a server operator placing or editing a sign that says  
[BANK] on the first line creates a modified sign that functions as a bank.  

### Shop  

Any player can make a shop by writing [SHOP] on the first line of a sign  
that is placed on the side of a (non shop) chest. On the second line, first  
write the prize for the shop, followed by a "/" and then what kind of shop it is.  
There is two kind of shops, Item or Stack. In item shop you can only buy one  
item at a time. In a stack shop, you buy however many items there is in the slot.  
The second line could look like "10.5/Item" or "20/Stack".  
The fourth line is optional, here you can add another player,  
if you wish to share the income with them.  

## Permissions  

`quickeconomy.balance` Allows the player to use the balance command and see their own balance. Default `true`  
`quickeconomy.balance.seeall`Allows the player to see all players balances. Default `false`  
`quickeconomy.balance.modifyall` Allows the player to modify all player balances. Default `false`  
`quickeconomy.help` Allows the player to use the quickeconomy command and get a help message. Default `true`  
`quickeconomy.shop` Allows the player to use shops. Default `true`  
`quickeconomy.shop.create` Allows the player to create shops. Default `true`  
`quickeconomy.shop.destroyall` Allows the player to remove all shops, not exclusively theirs. Default `false`  
`quickeconomy.bank` Allows the player to use banks. Default `true`  
`quickeconomy.bank.create` Allows the player to create banks. Default `false`  
`quickeconomy.bank.command` Allows the player to open the bank with command. Default `false`  
`quickeconomy.bank.destroy` Allows the player to destroy banks. Default `false`  
`quickeconomy.rollback` Allows the player to perform rollback on transactions. Default `false`  
`quickeconomy.migrate` Allows the player to migrate between file- and SQL-mode. Default `false`  
`quickeconomy.setup`  Allows the player to see the current plugin setup. Default `false`  
