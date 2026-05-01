class Person {
  final int? id;
  final String name;

  const Person({this.id, required this.name});

  factory Person.fromMap(Map<String, dynamic> map) =>
      Person(id: map['id'] as int?, name: map['name'] as String);

  Map<String, dynamic> toMap() => {if (id != null) 'id': id, 'name': name};
}
