Java sources generated with protoc 3.0.0-alpha-3 (javanano, `enum_style=java`) from
`protos/`, `proto_overrides/` and `proto_pixel/`. They are checked in so that the build does not
depend on the protobuf Gradle plugin. Regenerate them after changing a .proto file:

    protoc --javanano_out=enum_style=java,java_package=<file>|<package>:src_generated <file>.proto
