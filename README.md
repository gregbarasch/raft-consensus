# To issue commands
> Commands can be issued as strings from the command line

# Resources
- https://www.youtube.com/watch?v=LAqyTyNUYSY
- https://medium.freecodecamp.org/in-search-of-an-understandable-consensus-algorithm-a-summary-4bc294c97e0d
- https://container-solutions.com/raft-explained-part-33-safety-liveness-guarantees-conclusion/
- https://www.geeksforgeeks.org/raft-consensus-algorithm/
- http://thesecretlivesofdata.com/raft/
- https://thesquareplanet.com/blog/students-guide-to-raft/

# Notes on bugs and feature issues
- If any users don't respond to either requestVote or heartbeat, they will have to wait until the next one. No retry logic for this. Just a timed heartbeat
- I didnt start my index at 1 for the log, I started at 0, however I did handle this. I set certain indexs to -1 to compensate, and it fit well with the design
- I only handled single entry appending, no batch
- The ID that I use to save to file is incorrect, but I still have much work to be done...
- Sometimes the user input will timeout because it is expecting a command. I have not yet had time to fix this.
- If commitIndex > lastApplied at any point during execution, you should apply a particular log entry. It is not crucial that you do it straight away (for example, in the AppendEntries RPC handler), but it is important that you ensure that this application is only done by one entity. Specifically, you will need to either have a dedicated “applier”, or to lock around these applies, so that some other routine doesn’t also detect that entries need to be applied and also tries to apply.
- Make sure that you check for commitIndex > lastApplied either periodically, or after commitIndex is updated (i.e., after matchIndex is updated). For example, if you check commitIndex at the same time as sending out AppendEntries to peers, you may have to wait until the next entry is appended to the log before applying the entry you just sent out and got acknowledged.
- A leader is not allowed to update commitIndex to somewhere in a previous term (or, for that matter, a future term). Thus, as the rule says, you specifically need to check that log[N].term == currentTerm. This is because Raft leaders cannot be sure an entry is actually committed (and will not ever be changed in the future) if it’s not from their current term. This is illustrated by Figure 8 in the paper.
