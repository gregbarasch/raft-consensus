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
- If any users don't respond to either requestVote orr heartbeat, they will have to wait until the next one
- I did not implement a ds to relate requests to responses... I was not sure what this referred to.. Correlation ID?
- I didnt start my index at 1 for the log, I started at 0, however I did handle this. I set certain indexs to -1 to compensate, and it fit well with the design
- I only handled single entry appending, no batch
- I did not handle leader redirects and client response
- Timeouts are somewhat incorrect... Trying to fix... I realized too late: If election timeout elapses without receiving AppendEntries RPC from current leader or granting vote to candidate: convert to candidate.
- The ID that I use to save to file is incorrect, but I still have much work to be done...
- Sometimes the user input will timeout because it is expecting a command. I have not yet had time to fix this.