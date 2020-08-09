%define name    bootchart
%define version 0.9
#%define release 1jpp
%define release 1
%define section free

Name:           %{name}
Version:        %{version}
Release:        %{release}
Epoch:          0
Summary:        Boot Process Performance Visualization
License:        GPL
Url:            http://www.bootchart.org/
Source0:        http://www.bootchart.org/dist/SOURCES/%{name}-%{version}.tar.bz2
Group:          System/Benchmark
#Distribution:   JPackage
#Vendor:         JPackage Project
Requires:       jpackage-utils >= 0:1.5
Requires:       jakarta-commons-cli >= 0:1.0
BuildRequires:  ant
BuildRequires:  jpackage-utils >= 0:1.5
BuildRequires:  jakarta-commons-cli >= 0:1.0
BuildArch:      noarch
BuildRoot:      %{_tmppath}/%{name}-%{version}-buildroot


%description
A tool for performance analysis and visualization of the GNU/Linux boot
process. Resource utilization and process information are collected during
the boot process and are later rendered in a PNG, SVG or EPS encoded chart.

%package javadoc
Summary:        Javadoc for %{name}
Group:          Development/Documentation

%description javadoc
Javadoc for %{name}.

%package logger
Summary:        Boot logging script for %{name}
Group:          System/Boot

%define boottitle "Bootchart logging"

%description logger
Boot logging script for %{name}.

%prep
%setup -q

%build
# Remove the bundled commons-cli
rm -rf lib/org/apache/commons/cli lib/org/apache/commons/lang
CLASSPATH=%{_javadir}/commons-cli.jar ant

%install
rm -rf $RPM_BUILD_ROOT

# jar
install -D -m 644 %{name}.jar $RPM_BUILD_ROOT%{_javadir}/%{name}-%{version}.jar
ln -s %{name}-%{version}.jar $RPM_BUILD_ROOT%{_javadir}/%{name}.jar

# script
install -D -m 755 script/%{name} $RPM_BUILD_ROOT%{_bindir}/%{name}

# javadoc
install -d -m 755 $RPM_BUILD_ROOT%{_javadocdir}/%{name}-%{version}
cp -pr javadoc/* $RPM_BUILD_ROOT%{_javadocdir}/%{name}-%{version}
ln -s %{name}-%{version} $RPM_BUILD_ROOT%{_javadocdir}/%{name} # ghost symlink

# logger
install -D -m 755 script/bootchartd $RPM_BUILD_ROOT/sbin/bootchartd
install -D -m 644 script/bootchartd.conf $RPM_BUILD_ROOT/etc/bootchartd.conf

%clean
rm -rf $RPM_BUILD_ROOT

%post javadoc
rm -f %{_javadocdir}/%{name}
ln -s %{name}-%{version} %{_javadocdir}/%{name}

%post logger
# Add a new grub/lilo entry
if [ -x /sbin/grubby ]; then
        kernel=$(grubby --default-kernel)
        initrd=$(grubby --info=$kernel | sed -n '/^initrd=/{s/^initrd=//;p;q;}')
        [ ! -z $initrd ] && initrd="--initrd=$initrd"
        grubby --remove-kernel TITLE=%{boottitle}
        grubby --copy-default --add-kernel=$kernel $initrd --args="init=/sbin/bootchartd" --title=%{boottitle}
fi

%postun javadoc
if [ "$1" = "0" ]; then
    rm -f %{_javadocdir}/%{name}
fi

%preun logger
# Remove the grub/lilo entry
if [ -x /sbin/grubby ]; then
	grubby --remove-kernel TITLE=%{boottitle}
fi

%files
%defattr(0644,root,root,0755)
%doc ChangeLog COPYING INSTALL README TODO lib/LICENSE.cli.txt lib/LICENSE.compress.txt lib/LICENSE.epsgraphics.txt lib/NOTICE.txt
%{_javadir}/*
%dir %attr(0755,root,root) %{_bindir}/bootchart

%files javadoc
%defattr(0644,root,root,0755)
%doc %{_javadocdir}/%{name}-%{version}
%ghost %doc %{_javadocdir}/%{name}

%files logger
%defattr(0644,root,root,0755)
%doc README.logger
%attr(0755,root,root) /sbin/bootchartd
%config(noreplace) /etc/bootchartd.conf

%changelog
* Thu Jan 13 2005 Ziga Mahkovec <ziga.mahkovec@klika.si> - 0:0.8-1
- Initial release
