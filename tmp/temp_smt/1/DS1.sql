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
insert into classroom values ('Packard','3128','219')
insert into classroom values ('Taylor','120','10')
insert into department values ('Biology','V6','70791.47557038134')
insert into department values ('Physics','Taylor','109717.2058112')
insert into department values ('Music','Packard','64821.6838112')
insert into department values ('Finance','Painter','92321.821101')
insert into department values ('History','Taylor','109717.2058112')
insert into department values ('Biology','V6','70791.47557038134')
insert into department values ('Comp. Sci.','V5','109716.2058112')
insert into instructor values ('12121','Einstein',null,'40000.0')
insert into instructor values ('12121','Einstein',null,'40000.0')
insert into instructor values ('10101','Katz','Finance','88702.5')
insert into course values ('BIO-301','Music Video Production','Biology','3')
insert into course values ('CS-347','Music Video Production',null,'4')
insert into course values ('BIO-301','Music Video Production','Biology','3')
insert into course values ('CS-347','Music Video Production',null,'4')
insert into section values ('BIO-301','2','Spring','2010','Packard','3128','B')
insert into section values ('CS-347','1','Summer','2009','Taylor','120','C')
insert into teaches values ('10101','BIO-301','2','Spring','2010')
insert into teaches values ('12121','CS-347','1','Summer','2009')
