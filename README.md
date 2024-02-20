# QuickEconomy

An economy plugin for small Minecraft servers running PaperMC.  
![QuickEconomy Stats](https://bstats.org/signatures/bukkit/QuickEconomy.svg)

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
  
