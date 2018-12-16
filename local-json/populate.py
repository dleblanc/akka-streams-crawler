#!/usr/bin/env python

# Returns the URLs used for the children
def makeFileAtLevel(level, name):
    if level <= 0:
        return []

    reward = 1
    children = makeFileAtLevel(level - 1, name + ".f." + str(level - 1))
    children += makeFileAtLevel(level - 1, name + ".s." + str(level - 1))
    with open(name, "w") as f:
        f.write("""{
              "reward": %d,
              "children": [%s]
            }
            """ % (reward, ",".join(children)))

    return ["\"http://localhost:9000/" + name + "\""]


if __name__ == "__main__":
    makeFileAtLevel(8, "a")


