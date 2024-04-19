delete from prereq
delete from takes
delete from teaches
delete from section
delete from course
delete from advisor
delete from instructor
delete from student
delete from department
delete from classroom
insert into classroom values ('Packard','3128','170')
insert into classroom values ('Taylor','120','10')
insert into department values ('History','Taylor',null)
insert into department values ('Physics','Taylor','112614.1055')
insert into department values ('Finance','Painter','112615.1055')
insert into department values ('Finance','Painter','112615.1055')
insert into department values ('Music','Packard','108555.0')
insert into department values ('History','Taylor',null)
insert into department values ('Physics','Taylor','112614.1055')
insert into instructor values ('10101','Einstein',null,'53403.5')
insert into instructor values ('15151','Einstein','Finance','40000.0')
insert into instructor values ('12121','Katz','Finance',null)
insert into course values ('BIO-301','Music Video Production','History','3')
insert into course values ('CS-347','Music Video Production','Physics','3')
insert into course values ('BIO-301','Music Video Production','History','3')
insert into course values ('CS-347','Music Video Production','Physics','3')
insert into section values ('BIO-301','2','Spring','2010','Packard','3128','B')
insert into section values ('CS-347','1','Summer','2009','Taylor','120','B')
insert into teaches values ('12121','BIO-301','2','Spring','2010')
insert into teaches values ('10101','CS-347','1','Summer','2009')
