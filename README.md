# Antea IFC Export
Library to convert .eywa files to .ifc files.  
Class `tech.antea.ifc.Main` contains an example of usage.

### Usage
.eywa files can be deserialized with:
```java
import buildingsmart.ifc.IfcProject;
import com.fasterxml.jackson.databind.ObjectMapper;
import it.imc.persistence.po.eytukan.EywaRoot;
//...
    ObjectMapper objectMapper = new ObjectMapper();
    EywaRoot eywaRoot = objectMapper.readValue(eywaFile, EywaRoot.class);
```

The conversion between `EywaRoot` and `IfcProject` is done this way:
```java
    EywaToIfcConverter builder = new EywaToIfcConverter();
    EywaReader director = new EywaReader(builder);
    director.convert(eywaRoot);
    IfcProject result = builder.getResult();
```

Finally, .ifc files can be serialized with:
```java
    EywaToIfcConverter.writeToFile(result, outputFile);
```

### Tests and deterministic output
The content of .ifc files generated from the same input .eywa won't always be
the same, because of timestamps and random UUIDs. These are removed before
comparing output files and expected output files in tests, so there are no
issues there.  
Another issue could arise from elements of `Sets` being serialized in different
orders (because when iterating through a Set the order is generally
unpredictable), so at the moment `LinkedHashSets` are used, and they should keep
being used in future modifications for all `Sets` to avoid test failures.  
Another way to solve this issue would be writing a parser to create an
`IfcProject` from each expected output file, and then compare that to the
`IfcProject` generated from the actual output file (after removing timestamps
and UUIDs).

### Useful links
[IFC 2x3 documentation](https://standards.buildingsmart.org/IFC/RELEASE/IFC2x3/TC1/HTML/)  
[CoordinationView 2.0](https://standards.buildingsmart.org/MVD/RELEASE/IFC2x3/TC1/CV2_0/IFC2x3_CV2_0.zip)  
[Implementation agreements](https://standards.buildingsmart.org/documents/Implementation/IFC_Implementation_Agreements/)  
[EXPRESS specification (it's a more recent version than the one used in IFC, but they're similar)](https://www.steptools.com/stds/step/IS_final_p21e3.html)  
[Implementation guidance](https://technical.buildingsmart.org/resources/ifcimplementationguidance/)  